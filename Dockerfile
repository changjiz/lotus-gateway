FROM java:8

WORKDIR /app
COPY lotus-gateway.jar /app/lotus-gateway.jar

CMD java $JAVA_OPTS -jar /app/lotus-gateway.jar $APP_OPTS