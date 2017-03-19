#!/bin/bash

jar_location=$1
main_class=$2
property_filename=$3

echo "deploying topology"

echo $(storm jar $jar_location $main_class $property_filename)

if [ $? -eq 0 ]

then

echo "successfully deployed topology"

else

echo "Error in deploying topology"
exit 1

fi 
