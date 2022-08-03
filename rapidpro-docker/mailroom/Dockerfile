FROM debian:bullseye-slim as build

ARG MAILROOM_REPO
ENV MAILROOM_REPO ${MAILROOM_REPO}
ARG MAILROOM_VERSION
ENV MAILROOM_VERSION ${MAILROOM_VERSION}

RUN apt update && apt install -y wget
RUN wget -q -O mailroom.tar.gz "https://github.com/$MAILROOM_REPO/releases/download/v${MAILROOM_VERSION}/mailroom_${MAILROOM_VERSION}_linux_amd64.tar.gz"
RUN mkdir mailroom
RUN tar -xzC mailroom -f mailroom.tar.gz


FROM debian:bullseye-slim

RUN set -ex; \
    addgroup --system mailroom; \
    adduser --system --ingroup mailroom mailroom

# Install ca-certificates so HTTPS works in general
RUN apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates && \
    rm -rf /var/lib/apt/lists/*

COPY --from=build mailroom/mailroom /usr/local/bin
COPY --from=build mailroom/docs /docs

EXPOSE 8090

RUN mkdir _storage && chown mailroom _storage
USER mailroom

ENTRYPOINT []
CMD ["mailroom"]
