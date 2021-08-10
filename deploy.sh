#!/bin/sh
rm build/mirai/*.jar; ./gradlew buildPlugin; scp ./build/mirai/*.jar ten:~/mcl-1.2.2/plugins/mirai-landlord.jar
