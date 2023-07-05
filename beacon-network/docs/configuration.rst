Configuration
=============

Aggregator
----------

The aggregator can be configured with the configuration file located by default in ``beacon-network/aggregator/config/config.ini``.

Configuration File
~~~~~~~~~~~~~~~~~~

Configuration variables for setting up the web application are found in the ``[app]`` section.

.. literalinclude:: ../aggregator/config/config.ini
   :language: python
   :lines: 4-21

Configuration variables for defining the ``/service-info`` endpoint are found in the ``[info]`` section.

.. literalinclude:: ../aggregator/config/config.ini
   :language: python
   :lines: 23-54

Registries File
~~~~~~~~~~~~~~~

The ``registries.json`` file acts as the aggregator's database. The array can be populated by multiple objects, and the aggregator
will contact all of the listed ``url`` keys, which should point to ``/services`` endpoint at registries. If the aggregator is
registered as a service at the registry, the ``serviceKey`` from the registration response can be put into the ``key`` key, which 
allows the registry to call the ``DELETE /cache`` endpoint at the aggregator to remove the cached list of beacons from the aggregator.
This triggers the aggregator to request a new up-to-date list of beacons from the registry. This can be useful to let the aggregator know
about changes in the registry's service catalogue.

.. literalinclude:: ../aggregator/config/registries.json
   :language: javascript
   :lines: 1-6

Environment Variables
~~~~~~~~~~~~~~~~~~~~~

+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+
| ENV                   | Default        | Description                                                                                                                                                 |
+=======================+================+=============================================================================================================================================================+
| CONFIG_FILE           | config.ini     | Location of configuration file.                                                                                                                             |
+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+
| DEBUG                 | False          | Set to True to enable more debugging logs from functions.                                                                                                   |
+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+
| APP_HOST              | 0.0.0.0        | Application hostname.                                                                                                                                       |
+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+
| APP_PORT              | 8080           | Application port.                                                                                                                                           |
+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+
| APPLICATION_SECURITY  | 0              | Application security level, determines the SSL operating principle of  the server. Possible values are 0-2, more information in SSL Context  section below. |
+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+
| REQUEST_SECURITY      | 0              | Request security level, determines the SSL operating principle of  requests. Possible values are 0-2, more information in SSL Context   section below.      |
+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+
| PATH_SSL_CERT_FILE    | /etc/ssl/certs | Path to certificate.pem file.                                                                                                                               |
+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+
| PATH_SSL_KEY_FILE     | /etc/ssl/certs | Path to key.pem file.                                                                                                                                       |
+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+
| PATH_SSL_CA_FILE      | /etc/ssl/certs | Path to ca.pem file.                                                                                                                                        |
+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+
| APP_CORS              | *              | CORS domain, either a single domain or * for any domain.                                                                                                    |
+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+


Registry
--------

Configuration File
~~~~~~~~~~~~~~~~~~

Configuration variables for setting up the web application and database connection are found in the ``[app]`` section.

.. literalinclude:: ../registry/config/config.ini
   :language: python
   :lines: 4-31

Configuration variables for defining the ``/service-info`` endpoint are found in the ``[info]`` section.

.. literalinclude:: ../registry/config/config.ini
   :language: python
   :lines: 33-64

Environment Variables
~~~~~~~~~~~~~~~~~~~~~

+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+
| ENV                   | Default        | Description                                                                                                                                                 |
+=======================+================+=============================================================================================================================================================+
| CONFIG_FILE           | config.ini     | Location of configuration file.                                                                                                                             |
+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+
| DEBUG                 | False          | Set to True to enable more debugging logs from functions.                                                                                                   |
+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+
| APP_HOST              | 0.0.0.0        | Application hostname.                                                                                                                                       |
+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+
| APP_PORT              | 8080           | Application port.                                                                                                                                           |
+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+
| APPLICATION_SECURITY  | 0              | Application security level, determines the SSL operating principle of  the server. Possible values are 0-2, more information in SSL Context  section below. |
+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+
| REQUEST_SECURITY      | 0              | Request security level, determines the SSL operating principle of  requests. Possible values are 0-2, more information in SSL Context   section below.      |
+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+
| PATH_SSL_CERT_FILE    | /etc/ssl/certs | Path to certificate.pem file.                                                                                                                               |
+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+
| PATH_SSL_KEY_FILE     | /etc/ssl/certs | Path to key.pem file.                                                                                                                                       |
+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+
| PATH_SSL_CA_FILE      | /etc/ssl/certs | Path to ca.pem file.                                                                                                                                        |
+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+
| DB_HOST               | localhost      | Database address.                                                                                                                                           |
+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+
| DB_PORT               | 5432           | Database port.                                                                                                                                              |
+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+
| DB_USER               | user           | Username to access database.                                                                                                                                |
+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+
| DB_PASS               | pass           | Password for database user.                                                                                                                                 |
+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+
| DB_NAME               | db             | Database name.                                                                                                                                              |
+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+
| API_OTP               | True           | Boolean if API key at POST /services should be expired after use.                                                                                           |
+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+
| APP_CORS              | *              | CORS domain, either a single domain or * for any domain.                                                                                                    |
+-----------------------+----------------+-------------------------------------------------------------------------------------------------------------------------------------------------------------+


SSL
---

Experimental!! In production a reverse proxy is recommended.

Possible security levels for ``APPLICATION_SECURITY`` and ``REQUEST_SECURITY`` are 0-2.

+----------------+---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+---------------------------------------------------------------------------------------------------------------------------+
| Security Level | APPLICATION_SECURITY Behaviour                                                                                                                                                              | REQUEST_SECURITY Behaviour                                                                                                |
+================+=============================================================================================================================================================================================+===========================================================================================================================+
| 0              | Application is unsafe. API is served as HTTP.                                                                                                                                               | Application can make requests to HTTP (unsafe) and HTTPS (safe) resources.                                                |
+----------------+---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+---------------------------------------------------------------------------------------------------------------------------+
| 1              | Application is safe. API is served as HTTPS. This requires the use of PATH_SSL_* ENVs.                                                                                                      | Application can only make requests to HTTPS (safe) resources. Requests to HTTP (unsafe) resources are blocked.            |
+----------------+---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+---------------------------------------------------------------------------------------------------------------------------+
| 2              | Application belongs to a closed trust network. Applies same behaviour as security level 1. Application can only be requested from other applications that belong to the same trust network. | Application can only make requests to applications that belong to the same trust network (see previous cell description). |
+----------------+---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+---------------------------------------------------------------------------------------------------------------------------+
