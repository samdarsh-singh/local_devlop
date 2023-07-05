package main

import (
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/neicnordic/sensitive-data-archive/internal/broker"
	"github.com/neicnordic/sensitive-data-archive/internal/config"
	"github.com/neicnordic/sensitive-data-archive/internal/database"

	log "github.com/sirupsen/logrus"
)

// Export Conf so we can access it in the other modules
var Conf *config.Config

func main() {
	sigc := make(chan os.Signal, 5)
	signal.Notify(sigc, os.Interrupt, syscall.SIGHUP, syscall.SIGINT, syscall.SIGTERM, syscall.SIGQUIT)

	// Create a function to handle panic and exit gracefully
	defer func() {
		if err := recover(); err != nil {
			log.Fatal("Could not recover, exiting")
		}
	}()

	c, err := config.NewConfig()
	if err != nil {
		log.Error(err)
		sigc <- syscall.SIGINT
		panic(err)
	}
	Conf = c

	tlsProxy, err := config.TLSConfigProxy(Conf)
	if err != nil {
		log.Error(err)
		sigc <- syscall.SIGINT
		panic(err)
	}

	sdaDB, err := database.NewSDAdb(Conf.DB)
	if err != nil {
		log.Error(err)
		sigc <- syscall.SIGINT
		panic(err)
	}
	if sdaDB.Version < 4 {
		log.Error("database schema v4 is required")
		sigc <- syscall.SIGINT
		panic(err)
	}

	log.Debugf("Connected to sda-db (v%v)", sdaDB.Version)

	err = checkS3Bucket(Conf.S3)
	if err != nil {
		log.Error(err)
		sigc <- syscall.SIGINT
		panic(err)
	}

	messenger, err := broker.NewMQ(Conf.Broker)
	if err != nil {
		log.Error(err)
		sigc <- syscall.SIGINT
		panic(err)
	}

	log.Debug("messenger acquired ", messenger)

	go func() {
		<-sigc
		sdaDB.Close()
		messenger.Channel.Close()
		messenger.Connection.Close()
		os.Exit(1)
	}()
	var pubkeys map[string][]byte
	auth := NewValidateFromToken(pubkeys)
	auth.pubkeys = make(map[string][]byte)
	// Load keys for JWT verification
	if Conf.Server.Jwtpubkeyurl != "" {
		if err := auth.getjwtpubkey(Conf.Server.Jwtpubkeyurl); err != nil {
			log.Panicf("Error while getting key %s: %v", Conf.Server.Jwtpubkeyurl, err)
		}
	}
	if Conf.Server.Jwtpubkeypath != "" {
		if err := auth.getjwtkey(Conf.Server.Jwtpubkeypath); err != nil {
			log.Panicf("Error while getting key %s: %v", Conf.Server.Jwtpubkeypath, err)
		}
	}
	proxy := NewProxy(Conf.S3, auth, messenger, sdaDB, tlsProxy)

	log.Debug("got the proxy ", proxy)

	http.Handle("/", proxy)

	hc := NewHealthCheck(8001, sdaDB.DB, Conf, tlsProxy)
	go hc.RunHealthChecks()

	server := &http.Server{
		Addr:              ":8000",
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       30 * time.Second,
		ReadHeaderTimeout: 30 * time.Second,
	}

	if Conf.Server.Cert != "" && Conf.Server.Key != "" {
		if err := server.ListenAndServeTLS(Conf.Server.Cert, Conf.Server.Key); err != nil {
			panic(err)
		}
	} else {
		if err := server.ListenAndServe(); err != nil {
			panic(err)
		}
	}
}
