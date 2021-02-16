provider "aws" {
  allowed_account_ids = [
    281050902442]
  region = "eu-west-1"
  profile = "da-identity"
  assume_role {
    role_arn = "arn:aws:iam::281050902442:role/dA-Administrator"
  }
}

terraform {
  required_version = "~> 0.13"
  required_providers {
    aws = {
      source = "hashicorp/aws",
      version = "~> 3.28.0"
    }
  }
}