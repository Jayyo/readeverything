# Conversion server: Ubuntu's LibreOffice (with libreoffice-java-common, which
# H2Orestart needs to find Java) + the H2Orestart HWP/HWPX import filter.
FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
        libreoffice-core libreoffice-writer libreoffice-calc libreoffice-impress \
        libreoffice-java-common default-jre-headless \
        fonts-noto-cjk fonts-nanum \
        python3-flask python3-waitress ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*

# H2Orestart is GPL-3.0: https://github.com/ebandal/H2Orestart
ARG H2ORESTART_VERSION=0.7.13
ARG H2ORESTART_SHA256=726230215dabe450bd617f9acac52376fd76f57c77158bd03b3ef9fe0c7e64fd
RUN curl -fsSL -o /tmp/H2Orestart.oxt \
        "https://github.com/ebandal/H2Orestart/releases/download/v${H2ORESTART_VERSION}/H2Orestart.oxt" \
    && echo "${H2ORESTART_SHA256}  /tmp/H2Orestart.oxt" | sha256sum -c - \
    && unopkg add --shared --suppress-license /tmp/H2Orestart.oxt \
    && rm /tmp/H2Orestart.oxt

RUN useradd --create-home --uid 10001 convert
COPY convert_server.py /app/convert_server.py
USER convert
WORKDIR /home/convert
ENV HOST=0.0.0.0 PORT=3000 CONVERT_WORKDIR=/home/convert/work
EXPOSE 3000
CMD ["python3", "/app/convert_server.py"]
