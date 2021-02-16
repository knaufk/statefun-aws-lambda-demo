FROM openjdk:11-jre
COPY target/greeter-generator-0.0.1-fat.jar /usr/
WORKDIR /usr/
CMD ["java", "-cp", "/usr/greeter-generator-0.0.1-fat.jar", "com.github.knaufk.statefun.kinesis.UserKinesisProducer", "names", "names", "eu-west-1"]