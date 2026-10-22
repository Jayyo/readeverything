# Conversion server: Ubuntu + apt LibreOffice (proper Java integration via
# libreoffice-java-common, which gotenberg's self-built LibreOffice lacks) + H2Orestart
# for HWP/HWPX, wrapped by convert_server.py (soffice --convert-to pdf).
FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
        libreoffice-core libreoffice-writer libreoffice-calc libreoffice-impress \
        libreoffice-java-common default-jre-headless \
        fonts-noto-cjk fonts-noto-cjk-extra fonts-nanum \
        python3 python3-flask curl ca-certificates tzdata \
    && rm -rf /var/lib/apt/lists/*

# Register the HWP/HWPX import filter into LibreOffice's SHARED profile.
COPY H2Orestart.oxt /tmp/H2Orestart.oxt
RUN unopkg add --shared --suppress-license /tmp/H2Orestart.oxt

COPY convert_server.py /app/convert_server.py
ENV RE_PORT=3000 HOME=/root
EXPOSE 3000
CMD ["python3", "/app/convert_server.py"]
