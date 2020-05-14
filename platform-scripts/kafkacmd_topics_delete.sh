_mydir='/Users/alscott/RedHatTech/kafka_2.12-2.4.0.redhat-00005'
#_mydir='/Users/developer/RedHatTech/kafka_2.12-2.4.0.redhat-00005'

cd $_mydir
bin/kafka-topics.sh --delete --bootstrap-server localhost:9092 --replication-factor 1 --partitions 1 --topic opsMgmt_HL7_RcvdTrans &