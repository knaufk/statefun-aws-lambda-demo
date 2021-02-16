
variable "user_version" {
  type = string
}

variable "user_handler_function_name" {
  default = "user-handler"
}

variable "statefun-functions-bucket" {
  type = string
}

resource "aws_lambda_function" "greeter" {
  function_name = var.user_handler_function_name


  s3_bucket = var.statefun-functions-bucket
  s3_key    = "functions/${var.user_handler_function_name}-${var.user_version}-fat.jar"


  handler = "com.github.knaufk.statefun.UserHandler"
  runtime = "java11"

  memory_size = 512
  timeout = 15

  role = aws_iam_role.greeter_fun_role.arn

  depends_on = [
    aws_iam_role_policy_attachment.greeter_logging_policy_attachment,
    aws_cloudwatch_log_group.greeter_log_group,
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
resource "aws_lambda_permission" "greeter_gw_permissions" {
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.greeter.arn
  principal     = "apigateway.amazonaws.com"

  source_arn = "${aws_apigatewayv2_api.api.execution_arn}/*/*"
}

resource "aws_cloudwatch_log_group" "greeter_log_group" {
  name              = "/aws/lambda/${var.user_handler_function_name}"
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

resource "aws_iam_role_policy_attachment" "greeter_logging_policy_attachment" {
  role       = aws_iam_role.greeter_fun_role.name
  policy_arn = aws_iam_policy.lambda_logging.arn
}
