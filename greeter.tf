
variable "lambda_function_version" {
  default = "0.0.1"
}

variable "lambda_function_name" {
  default = "greeter"
}

# S3 Bucket for Code
resource "aws_s3_bucket" "statefun-functions-eu-west-1" {
  bucket = "statefun-functions-eu-west-1"
}

resource "aws_lambda_function" "greeter" {
  function_name = var.lambda_function_name


  s3_bucket = aws_s3_bucket.statefun-functions-eu-west-1.bucket
  s3_key    = "functions/${var.lambda_function_name}-${var.lambda_function_version}-fat.jar"


  handler = "com.github.knaufk.statefun.GreeterHandler"
  runtime = "java11"

  memory_size = 512
  timeout = 15

  role = aws_iam_role.greeter_fun_role.arn

  depends_on = [
    aws_iam_role_policy_attachment.lambda_logs,
    aws_cloudwatch_log_group.example,
  ]
}

resource "aws_iam_role" "greeter_fun_role" {
  name = "greeter_fun_role"

  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Principal": {
        "Service": "lambda.amazonaws.com"
      },
      "Effect": "Allow"
    }
  ]
}
EOF
}

# Permission
resource "aws_lambda_permission" "apigw" {
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.greeter.arn
  principal     = "apigateway.amazonaws.com"

  source_arn = "${aws_apigatewayv2_api.api.execution_arn}/*/*"
}

resource "aws_cloudwatch_log_group" "example" {
  name              = "/aws/lambda/${var.lambda_function_name}"
  retention_in_days = 14
}

# See also the following AWS managed policy: AWSLambdaBasicExecutionRole
resource "aws_iam_policy" "lambda_logging" {
  name        = "lambda_logging"
  path        = "/"
  description = "IAM policy for logging from a lambda"

  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "arn:aws:logs:*:*:*",
      "Effect": "Allow"
    }
  ]
}
EOF
}

resource "aws_iam_role_policy_attachment" "lambda_logs" {
  role       = aws_iam_role.greeter_fun_role.name
  policy_arn = aws_iam_policy.lambda_logging.arn
}
