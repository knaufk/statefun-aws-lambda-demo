import base64

from statefun import *

functions = StatefulFunctions()

USER_PROFILE_TYPE = make_json_type(typename="com.knaufk/UserProfile")

@functions.bind(typename="com.knaufk.fns/greeter")
def greeter(context: Context, message: Message):

    profile = message.as_type(USER_PROFILE_TYPE)
    name = profile['name']
    seen_count = profile['seenCount']
    last_seen_ms = profile['lastSeenMs']

    context.send_egress(kinesis_egress_message(typename="com.knaufk/greets",
                                               stream="greetings",
                                               value=f'Hello {name}. Nice to see you for the {seen_count}th time! '
                                                     f'It has been {last_seen_ms} milliseconds since we last saw you.',
                                               partition_key=name))

handler = RequestReplyHandler(functions)


def build_response(response_bytes):
    response_base64 = base64.b64encode(response_bytes).decode('ascii')
    response = {
        "isBase64Encoded": True,
        "statusCode": 200,
        "headers": {"Content-Type": "application/octet-stream"},
        "multiValueHeaders": {},
        "body": response_base64
    }
    return response


def decode_request(request):
    return base64.b64decode(request["body"])


def handle(request, context):
    message_bytes = decode_request(request)
    response_bytes = handler.handle_sync(message_bytes)
    return build_response(response_bytes)
