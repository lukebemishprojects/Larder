FROM node:26.5.0-alpine3.24 AS nodejsbuilder
WORKDIR /opt/larder
COPY --exclude=dist --exclude=node_modules ./dashboard/ ./dashboard/
COPY --exclude=dist --exclude=node_modules ./indices/ ./indices/
RUN --mount=type=cache,target=/root/.npm \
    npm install --prefix indices && \
    npm install --prefix dashboard && \
    npm run --prefix indices build && \
    npm run --prefix dashboard build

FROM amazoncorretto:26.0.1-alpine3.24 AS jvmsource
RUN rm -rf /lib/apk

FROM gradle:9.7.0-jdk25-alpine AS gradlebuilder
WORKDIR /opt/larder
COPY --exclude=**/build --exclude=**/.idea --exclude=**/.git --exclude=**/.gradle --exclude=indices --exclude=dashboard --exclude=Dockerfile ./ ./
COPY --from=nodejsbuilder /opt/larder/dashboard/ ./dashboard/
COPY --from=nodejsbuilder /opt/larder/indices/ ./indices/
COPY --from=jvmsource /usr/lib/jvm/java-25-amazon-corretto/ /usr/lib/jvm/java-25-amazon-corretto/
RUN --mount=type=cache,target=/root/.gradle \
    gradle --no-daemon -Pdev.lukebemish.larder.buildtooling.assume-npm-prebuilt=true jlink
RUN rm -rf /tmp/*

FROM scratch
COPY --from=jvmsource /lib/ /lib/
COPY --from=jvmsource /usr/lib/libz* /usr/lib/
COPY --from=gradlebuilder /tmp/ /tmp/
COPY --from=gradlebuilder /opt/larder/build/jlink/larder/ /opt/larder/
CMD ["/opt/larder/bin/java", "-m","dev.lukebemish.larder"]
