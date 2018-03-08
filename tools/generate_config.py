import json
import argparse

parser = argparse.ArgumentParser(description="Generates configs for Loopix and JLoopix")
parser.add_argument('--rate', type=float, help='Sets the rate parameter for messages', default=1/2.0)
parser.add_argument('--delay', type=float, help='Sets the delay parameter for messages', default=0.001)
args = parser.parse_args()

scale = 1/args.rate
scalestr = str(scale)
delay = args.delay
delaystr = str(delay)

# jloopix config
jconfig = {
    "NOISE_LENGTH": 500,
    "EXP_PARAMS_DROP": scale,
    "PATH_LENGTH": 3,
    "EXP_PARAMS_LOOPS": scale,
    "EXP_PARAMS_DELAY": delay,
    "TIME_PULL": 10,
    "EXP_PARAMS_PAYLOAD": scale,
    "DATABASE_NAME": "example.db",
    "DATA_DIR": "debug"
}

config = {
    "parametersClients": {
        "NOISE_LENGTH": "500",
        "EXP_PARAMS_DROP": scalestr,
        "PATH_LENGTH": "3",
        "EXP_PARAMS_LOOPS": scalestr,
        "EXP_PARAMS_DELAY": delaystr,
        "TIME_PULL": "10.0",
        "EXP_PARAMS_PAYLOAD": scalestr,
        "DATABASE_NAME": "example.db",
        "DATA_DIR": "tmp/mail"
    },
    "parametersMixnodes": {
        "NOISE_LENGTH": "500",
        "EXP_PARAMS_LOOPS": scalestr,
        "EXP_PARAMS_DELAY": delaystr,
        "MAX_DELAY_TIME": "-432000",
        "DATABASE_NAME": "example.db"
    },
    "parametersProviders": {
        "MAX_RETRIEVE": "50",
        "NOISE_LENGTH": "500",
        "EXP_PARAMS_LOOPS": scalestr,
        "EXP_PARAMS_DELAY": delaystr,
        "MAX_DELAY_TIME": "-432000",
        "DATABASE_NAME": "example.db"
    }
}

with open('../build/loopix_config.json', 'w+') as f:
    json.dump(config, f)

with open('../build/jloopix_config.json', 'w+') as f:
    json.dump(jconfig, f)