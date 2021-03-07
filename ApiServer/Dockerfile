FROM golang:alpine AS builder

WORKDIR /build

COPY main.go .

RUN apk update && \
    apk add git && \
    go env -w GO111MODULE=auto CGO_ENABLED=0 GOOS=linux GOARCH=amd64 && \
    go get -d -v && \
    go build -ldflags="-w -s" -o AnimeDBApi .

FROM busybox AS builder-user

RUN addgroup -g 10002 appUser && \
    adduser -D -u 10003 -G appUser appUser

FROM scratch
COPY --from=builder /build/AnimeDBApi /AnimeDBApi
COPY --from=builder-user /etc/passwd /etc/passwd

ENV IN_DOCKER=1 \
    SOLR_ADDR="http://localhost:8988/solr" \
    AUTH_KEY="00000000-0000-0000-0000-000000000000"

ENV PORT 3010
EXPOSE 3010

USER appUser
ENTRYPOINT ["/AnimeDBApi"]
