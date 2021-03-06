import json
import sys
import argparse

parser = argparse.ArgumentParser(
    description="Sets up the network configuration")
parser.add_argument('--rate-real', type=float,
                    help='Sets the rate parameter for real messages. Parameter is the rate of messages per second (lambda).', 
                    default=1 / 2.0)
parser.add_argument('--rate-drop', type=float,
                    help='Sets the rate parameter for drop messages. Parameter is the rate of messages per second (lambda).',
                    default=1 / 2.0)
parser.add_argument('--rate-loop', type=float,
                    help='Sets the rate parameter for loop messages. Parameter is the rate of messages per second (lambda).',
                    default=1 / 2.0)

parser.add_argument('--delay', type=float,
                    help='Sets the delay parameter for messages. Parameter is the average delay per hop in seconds (1/mu). Should be >= 2/lambda.', 
                    default=0.001)

parser.add_argument('--mix', type=int,
                    help='Sets the number of mix nodes', default=6)
parser.add_argument('--provider', type=int,
                    help='Sets the number of provider nodes', default=2)
parser.add_argument('--client', type=int,
                    help='Sets the number of python client nodes', default=5)
parser.add_argument('--client-java', type=int,
                    help='Sets the number of java client nodes', default=1) 

parser.add_argument('--push', type=bool,
                    help='Sets provider behaviour when receiving messages', default=False)

parser.add_argument('--time-pull', type=int,
                    help='Sets the time between PULLs from provider', default=10)
parser.add_argument('--max-retrieve', type=int,
                    help='Sets the number of messages pulled', default=50)
args = parser.parse_args()

# Write network_config.sh with node counts
with open('network_config.sh', 'w') as f:
    f.writelines([
        'MIXNODE_COUNT=%d\n' % args.mix,
        'PROVIDER_COUNT=%d\n' % args.provider,
        'CLIENT_COUNT=%d\n' % args.client,
        'JAVA_COUNT=%d' % args.client_java
    ])

scale_real = 1/args.rate_real
scale_real_str = str(scale_real)
scale_drop = 1/args.rate_drop
scale_drop_str = str(scale_drop)
scale_loop = 1 / args.rate_loop
scale_loop_str = str(scale_loop)

delay = args.delay
delay_str = str(delay)

# jloopix config
jconfig = {
    "NOISE_LENGTH": 500,
    "EXP_PARAMS_DROP": scale_drop,
    "PATH_LENGTH": 3,
    "EXP_PARAMS_LOOPS": scale_loop,
    "EXP_PARAMS_DELAY": delay,
    "TIME_PULL": args.time_pull,
    "EXP_PARAMS_PAYLOAD": scale_real,
    "DATABASE_NAME": "example.db",
    "DATA_DIR": "debug"
}

config = {
    "parametersClients": {
        "NOISE_LENGTH": "500",
        "EXP_PARAMS_DROP": scale_drop_str,
        "PATH_LENGTH": "3",
        "EXP_PARAMS_LOOPS": scale_loop_str,
        "EXP_PARAMS_DELAY": delay_str,
        "TIME_PULL": str(args.time_pull),
        "EXP_PARAMS_PAYLOAD": scale_real_str,
        "DATABASE_NAME": "example.db",
        "DATA_DIR": "tmp/mail"
    },
    "parametersMixnodes": {
        "NOISE_LENGTH": "500",
        "EXP_PARAMS_LOOPS": scale_loop_str,
        "EXP_PARAMS_DELAY": delay_str,
        "MAX_DELAY_TIME": "-432000",
        "DATABASE_NAME": "example.db"
    },
    "parametersProviders": {
        "MAX_RETRIEVE": "0" if args.push else str(args.max_retrieve),
        "NOISE_LENGTH": "500",
        "EXP_PARAMS_LOOPS": scale_loop_str,
        "EXP_PARAMS_DELAY": delay_str,
        "MAX_DELAY_TIME": "-432000",
        "DATABASE_NAME": "example.db",
        "PUSH_MESSAGES": args.push
    }
}

with open('../build/loopix_config.json', 'w+') as f:
    json.dump(config, f, indent=4)

with open('../build/jloopix_config.json', 'w+') as f:
    json.dump(jconfig, f, indent=4)
