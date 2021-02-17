# HTTP API
resource "aws_apigatewayv2_api" "api" {
  name          = "StatefulFunctions"
  protocol_type = "HTTP"
  target = aws_lambda_function.greeter.arn
}

//resource "aws_apigatewayv2_integration" "greeter" {
//  api_id           = aws_apigatewayv2_api.api.id
//  integration_type = "AWS_PROXY"
//
//  integration_method        = "POST"
//  integration_uri           = aws_lambda_function.greeter.invoke_arn
//}
//
//resource "aws_apigatewayv2_route" "greeter" {
//  api_id    = aws_apigatewayv2_api.api.id
//  route_key = "ANY /greeter/{proxy+}"
//
//  target = "integrations/${aws_apigatewayv2_integration.greeter.id}"
//}
//
//resource "aws_apigatewayv2_stage" "default" {
//  api_id = aws_apigatewayv2_api.api.id
//  name   = "default"
//  auto_deploy = true
//}

output "base_url" {
  value = aws_apigatewayv2_api.api.api_endpoint
}
