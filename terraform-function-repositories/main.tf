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
      version = "~> 3.28.0"
    }
  }

  backend "s3" {
    bucket = "statefun-aws-terraform-state"
    key     = "function-hosting"
  }

}