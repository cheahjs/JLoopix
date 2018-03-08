# Use the Ubuntu 16.04 base image
FROM ubuntu:xenial

# Update
RUN yes | apt update
RUN yes | apt install python2.7 python-pip python-dev build-essential \
        libssl-dev libffi-dev

# Install app dependencies
RUN pip install "numpy >= 1.9.1"
RUN pip install "twisted >= 15.5.0"
RUN pip install "petlib >= 0.0.34"
RUN pip install "sphinxmix>=0.0.6"

# Add loopix source files
# Potentially just clone
ADD loopix /loopix