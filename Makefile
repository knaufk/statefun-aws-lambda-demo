# define the name of the virtual environment directory
PLAN := .terraform/PLAN

export AWS_ACCOUNT=
export AWS_REGION=
export AWS_PROFILE=

export TF_VAR_snapshots_s3_bucket=
export TF_VAR_statefun-functions-bucket=

export TF_VAR_user_version=0.0.2
export TF_VAR_greeter_version=0.0.3

export GATEWAY_ID=

DOCKER := /bin/docker

terraform-function-repositories: terraform-plan-function-repositories terraform-apply-function-repositories

terraform-init-function-repositories:
	terraform -chdir=terraform-function-repositories/ init

terraform-plan-function-repositories: terraform-init-function-repositories
	terraform -chdir=terraform-function-repositories/ plan -out "${PLAN}" -var="account_id=$(AWS_ACCOUNT)"

terraform-apply-function-repositories: terraform-init-function-repositories
	@if [ ! -e "terraform-function-repositories/${PLAN}" ]; then\
		echo >&2 "error: run 'make plan' first";\
		exit 1;\
	fi
	@if [ "$$(find "terraform-function-repositories/${PLAN}" -mmin -5)" = "" ]; then\
		echo >&2 "error: plan older than 5min; please run 'make plan' again";\
		exit 1;\
	fi
	terraform -chdir=terraform-function-repositories apply "${PLAN}"
	rm -f "${PLAN}"

terraform-destroy-function-repositories: terraform-init-function-repositories
	terraform -chdir=terraform-function-repositories/ destroy -var="account_id=$(AWS_ACCOUNT)"

terraform-statefun: functions terraform-plan-statefun terraform-apply-statefun

terraform-init-statefun:
	terraform -chdir=terraform-statefun init

terraform-plan-statefun: terraform-init-statefun
	terraform -chdir=terraform-statefun plan -out "${PLAN}" -var="account_id=$(AWS_ACCOUNT)"

terraform-apply-statefun: terraform-init-statefun
	@if [ ! -e "terraform-statefun/${PLAN}" ]; then\
		echo >&2 "error: run 'make plan' first";\
		exit 1;\
	fi
	@if [ "$$(find "terraform-statefun/${PLAN}" -mmin -5)" = "" ]; then\
		echo >&2 "error: plan older than 5min; please run 'make plan' again";\
		exit 1;\
	fi
	terraform -chdir=terraform-statefun apply "${PLAN}"
	rm -f "${PLAN}"

terraform-destroy-statefun:
	terraform -chdir=terraform-statefun destroy -var="account_id=$(AWS_ACCOUNT)" terraform-statefun/

functions: terraform-function-repositories login greeter-image upload-user-function

generator: terraform-function-repositories login generator-image

login:
	aws ecr get-login-password --region eu-west-1 --profile $(AWS_PROFILE) | docker login --username AWS --password-stdin $(AWS_ACCOUNT).dkr.ecr.$(AWS_REGION).amazonaws.com

generator-image:
	mvn -f generator/pom.xml clean package; \
	$(DOCKER) build generator/. -t  $(AWS_ACCOUNT).dkr.ecr.$(AWS_REGION).amazonaws.com/greeter-generator:0.0.1; \
    $(DOCKER) push $(AWS_ACCOUNT).dkr.ecr.$(AWS_REGION).amazonaws.com/greeter-generator:0.0.1

greeter-image:
	$(DOCKER) build greeter-handler/. -t  $(AWS_ACCOUNT).dkr.ecr.$(AWS_REGION).amazonaws.com/greeter:$(TF_VAR_greeter_version); \
    $(DOCKER) push $(AWS_ACCOUNT).dkr.ecr.$(AWS_REGION).amazonaws.com/greeter:$(TF_VAR_greeter_version)

k8s: k8s-update-context k8s-generator k8s-statefun

k8s-update-context:
	aws eks update-kubeconfig  --name statefun-eks --region $(AWS_REGION) --profile $(AWS_PROFILE)

k8s-statefun:
	kubectl apply -f k8s/statefun/resources; \
	envsubst < k8s/statefun/resources/flink-config.yaml.template | kubectl apply -f -
	envsubst < k8s/statefun/resources/greeter-module.yaml.template | kubectl apply -f -

k8s-generator:
	kubectl apply -f k8s/generator/resources
	envsubst < k8s/generator/resources/greeter-generator.yaml.template | kubectl apply -f -

upload-user-function:
	mvn -f user-handler/pom.xml clean package; \
	aws s3 cp user-handler/target/user-handler-$(TF_VAR_user_version)-fat.jar s3://$(TF_VAR_statefun-functions-bucket)/functions/user-handler-$(TF_VAR_user_version)-fat.jar

clean-buckets:
	aws s3 rm s3://$(TF_VAR_statefun-functions-bucket) --recursive; \
	aws s3 rm s3://$(TF_VAR_snapshots_s3_bucket) --recursive

cleanup: clean-functions-bucket destroy

.PHONY: images generator-image python-greeter-image k8s-statefun k8s-generator apply init