import os.path
import sqlite3
import petlib.pack
from glob import glob


class DatabaseManager(object):
    def __init__(self, databaseName):
        self.db = sqlite3.connect(databaseName)
        self.cursor = self.db.cursor()

    def create_users_table(self, table_name):
        self.cursor.execute('''CREATE TABLE IF NOT EXISTS %s (id INTEGER PRIMARY KEY,
                            name blob,
                            port integer,
                            host text,
                            pubk blob,
                            provider blob)''' % table_name)
        self.db.commit()
        print "Table [%s] created successfully." % table_name

    def create_providers_table(self, table_name):
        self.cursor.execute('''CREATE TABLE IF NOT EXISTS %s (id INTEGER PRIMARY KEY,
                            name blob,
                            port integer,
                            host text,
                            pubk blob)''' % table_name)
        self.db.commit()
        print "Table [%s] created successfully." % table_name

    def create_mixnodes_table(self, table_name):
        self.cursor.execute('''CREATE TABLE IF NOT EXISTS %s (id INTEGER PRIMARY KEY,
                            name blob,
                            port integer,
                            host text,
                            pubk blob,
                            groupId integer)''' % table_name)
        self.db.commit()
        print "Table [%s] created successfully." % table_name

    def drop_table(self, table_name):
        self.cursor.execute("DROP TABLE IF EXISTS %s" % table_name)
        print "Table [%s] dropped successfully." % table_name

    def insert_row_into_table(self, table_name, params):
        insert_query = "INSERT INTO %s VALUES (%s)" % (
            table_name, ', '.join('?' for p in params))
        print "Running query: %s" % insert_query
        self.cursor.execute(insert_query, params)
        self.db.commit()

    def close_connection(self):
        self.db.close()


def readPacked(file_name):
    data = file(file_name, "rb").read()
    return petlib.pack.decode(data)


if os.path.isfile('../build/example.db'):
    os.remove('../build/example.db')

manager = DatabaseManager('../build/example.db')
manager.create_users_table('Users')
manager.create_providers_table('Providers')
manager.create_mixnodes_table('Mixnodes')

for pub_file in glob('../build/loopix_keys/client_*/public*'):
    _, name, port, host, pubk, prvinfo = readPacked(pub_file)
    manager.insert_row_into_table('Users',
                                  [None, name, port, host,
                                   sqlite3.Binary(
                                       petlib.pack.encode(pubk)),
                                   prvinfo])

for pub_file in glob('../build/loopix_keys/provider_*/public*'):
    _, name, port, host, pubk = readPacked(pub_file)
    manager.insert_row_into_table('Providers',
                                  [None, name, port, host,
                                   sqlite3.Binary(petlib.pack.encode(pubk))])

for pub_file in glob('../build/loopix_keys/mixnode_*/public*'):
    _, name, port, host, group, pubk = readPacked(pub_file)
    manager.insert_row_into_table('Mixnodes',
                                  [None, name, port, host,
                                   sqlite3.Binary(petlib.pack.encode(pubk)), group])

manager.close_connection()