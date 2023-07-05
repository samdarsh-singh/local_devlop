FROM mongo:6-jammy
MAINTAINER Manuel Rueda <manuel.rueda@cnag.crg.eu>

# Build env
RUN apt-get update && \
    apt-get -y install apt-utils wget bzip2 git cpanminus perl-doc gcc make libbz2-dev zlib1g-dev libncurses5-dev libncursesw5-dev liblzma-dev libcurl4-openssl-dev pkg-config libssl-dev aria2 unzip jq vim sudo default-jre python3-pip && \
    pip install xlsx2csv

# Install bcftools
RUN cd /tmp \
    && wget https://github.com/samtools/bcftools/releases/download/1.15.1/bcftools-1.15.1.tar.bz2 \
    && tar -xvjf bcftools-1.15.1.tar.bz2 \
    && cd bcftools-1.15.1 \
    && ./configure && make && make install \
    && rm -rf /tmp/bcftools-1.15.1*

# Copy the files from the beacon2-ri-tools project:
WORKDIR /usr/share/beacon-ri/beacon2-ri-tools
COPY . .
RUN cpanm --sudo --installdeps .

# Set the Docker image execution context:
WORKDIR /usr/share/beacon-ri
ENTRYPOINT ["/usr/bin/bash"]
CMD []
