FROM openjdk:11-jre
COPY target/greeter-generator-*-fat.jar /usr/app.jar
CMD ["java", "-cp", "/usr/app.jar", "com.github.knaufk.statefun.kinesis.UserKinesisProducer", "names", "names", "eu-west-1"]