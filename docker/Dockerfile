# Use the Alpine base image
FROM alpine:3.6

# Update
RUN echo @testing http://dl-cdn.alpinelinux.org/alpine/edge/testing >> /etc/apk/repositories
RUN apk add --update \
        python \
        py-pip \
        gcc \
        g++ \
        curl \
        openssl-dev \
        python-dev \
        libffi-dev \
        make \
        libpng-dev \
        freetype-dev \
        py-numpy-dev \
        gfortran \
        openblas-dev \
        cython-dev

# Install app dependencies
RUN pip install "numpy >= 1.9.1"
RUN pip install "pytest >= 2.6.4"
RUN pip install "twisted >= 15.5.0"
RUN pip install "msgpack-python >= 0.4.6"
RUN pip install "petlib >= 0.0.34"
RUN pip install "sphinxmix>=0.0.6"
RUN pip install "fabric>=1.12.0"
RUN pip install "boto3>=1.4.0"
RUN pip install "matplotlib>=1.4.2"
RUN pip install "scipy>=0.16.1"
RUN pip install "scapy>=2.3.3"
RUN pip install "pybloom>=1.1"

# Add loopix source files
# Potentially just clone
ADD external/loopix /loopix