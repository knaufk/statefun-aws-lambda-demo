
variable "greeter_version" {
  type = string
}

variable "greeter_function_name" {
  default = "greeter"
}

resource "aws_lambda_function" "python_greeter" {
  function_name = var.greeter_function_name

  package_type="Image"
  image_uri = "${var.account_id}.dkr.ecr.eu-west-1.amazonaws.com/greeter:${var.greeter_version}"

  memory_size = 512
  timeout = 15

  role = aws_iam_role.python_greeter_fun_role.arn

  depends_on = [
    aws_iam_role_policy_attachment.python_greeter_logging_policy_attachment,
    aws_cloudwatch_log_group.python_greeter_log_group,
  ]
}

resource "aws_iam_role" "python_greeter_fun_role" {
  name = "python_greeter_fun_role"

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
resource "aws_lambda_permission" "python_greeter_gw_permissions" {
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.python_greeter.arn
  principal     = "apigateway.amazonaws.com"

  source_arn = "${aws_apigatewayv2_api.api.execution_arn}/*/*"
}

resource "aws_cloudwatch_log_group" "python_greeter_log_group" {
  name              = "/aws/lambda/${var.greeter_function_name}"
  retention_in_days = 14
}

resource "aws_iam_role_policy_attachment" "python_greeter_logging_policy_attachment" {
  role       = aws_iam_role.python_greeter_fun_role.name
  policy_arn = aws_iam_policy.lambda_logging.arn
}
