variable "account_id" {
  type = string
}

provider "aws" {
  allowed_account_ids = [var.account_id]
}

terraform {
  required_version = "~> 0.13"
  required_providers {
    aws = {
      source = "hashicorp/aws",
    }
  }

  backend "s3" {
    bucket = "statefun-aws-terraform-state"
    key     = "statefun"
  }

}