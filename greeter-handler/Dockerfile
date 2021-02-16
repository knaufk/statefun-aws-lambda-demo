FROM public.ecr.aws/lambda/python:3.8

COPY requirements.txt /tmp/
RUN pip install -r /tmp/requirements.txt
COPY functions.py   ./
CMD ["functions.handle"]