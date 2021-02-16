###########
#   ECR   #
###########

variable "statefun-functions-bucket" {
  type = string
}

resource "aws_ecr_repository" "greeter-generator" {
  name                 = "greeter-generator"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_repository" "greeter" {
  name = "greeter"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

# S3 Bucket for Code
resource "aws_s3_bucket" "statefun-functions-bucket" {
  bucket = var.statefun-functions-bucket
}
