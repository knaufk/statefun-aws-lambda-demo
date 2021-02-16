```
aws ecr create-repository  --repository-name greeter-generator  --image-scanning-configuration scanOnPush=true  --region eu-west-1 --profile da-fe
aws ecr get-login-password --region eu-west-1 --profile da-fe | docker login --username AWS --password-stdin 281050902442.dkr.ecr.eu-west-1.amazonaws.com  
docker build . -t  281050902442.dkr.ecr.eu-west-1.amazonaws.com/greeter-generator:0.0.1
docker push 281050902442.dkr.ecr.eu-west-1.amazonaws.com/greeter-generator:0.0.1
```