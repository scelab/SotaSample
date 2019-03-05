classpath=".:/home/vstone/lib/*\
:/home/vstone/vstonemagic/*\
:/usr/local/share/OpenCV/java/*\
:/home/root/SotaSample/lib/*\
"

OPTION="-Dfile.encoding=UTF8 -Djava.library.path=/usr/local/share/OpenCV/java/"
AMQ_IP="192.168.11.7"
AMQ_PORT="61616"
ROBOT_ID=$1
IS_USE_3D_SENSOR=$2

echo "java -classpath $classpath $OPTION"
java -classpath "$classpath" $OPTION jp/vstone/sotamain/Main &
#java -classpath "$classpath" $OPTION jp/vstone/sotasample/amqmodule/LookExecutor      $AMQ_IP $AMQ_PORT $ROBOT_ID &
#java -classpath "$classpath" $OPTION jp/vstone/sotasample/amqmodule/FaceTracker       $AMQ_IP $AMQ_PORT $ROBOT_ID &
#java -classpath "$classpath" $OPTION jp/vstone/sotasample/amqmodule/SpeechRecognizer  $AMQ_IP $AMQ_PORT $ROBOT_ID &
#java -classpath "$classpath" $OPTION jp/vstone/sotasample/amqmodule/UtteranceExecutor $AMQ_IP $AMQ_PORT $ROBOT_ID &