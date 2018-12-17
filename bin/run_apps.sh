classpath=".:/home/vstone/lib/*\
:/home/vstone/vstonemagic/*\
:/usr/local/share/OpenCV/java/*\
:/home/root/SotaSample/lib/*\
"

OPTION="-Dfile.encoding=UTF8 -Djava.library.path=/usr/local/share/OpenCV/java/"
AMQ_IP="192.168.11.5"
AMQ_PORT="61616"
ROBOT_ID=$1

echo "java -classpath $classpath $OPTION"
java -classpath "$classpath" $OPTION jp/vstone/sotasample/amqmodule/AxesController   $AMQ_IP $AMQ_PORT $ROBOT_ID &
java -classpath "$classpath" $OPTION jp/vstone/sotasample/amqmodule/FaceTracker      $AMQ_IP $AMQ_PORT $ROBOT_ID &
java -classpath "$classpath" $OPTION jp/vstone/sotasample/amqmodule/SpeechRecognizer $AMQ_IP $AMQ_PORT $ROBOT_ID &
java -classpath "$classpath" $OPTION jp/vstone/sotasample/amqmodule/WavePlayer       $AMQ_IP $AMQ_PORT $ROBOT_ID &