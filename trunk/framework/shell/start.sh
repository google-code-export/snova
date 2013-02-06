#!/bin/bash

#this part is copied from ANt's script
# OS specific support.  $var _must_ be set to either true or false.
cygwin=false;
case "`uname`" in
  CYGWIN*) cygwin=true ;;
esac

SNOVA_BIN=`dirname $0 | sed -e "s#^\\([^/]\\)#${PWD}/\\1#"` # sed makes absolute
SNOVA_HOME=$SNOVA_BIN/..
SNOVA_LIB=$SNOVA_HOME/lib
SNOVA_CONFIG=$SNOVA_HOME/conf
CLASSPATH="$SNOVA_HOME/lib/snova.jar:$SNOVA_CONFIG"
if $cygwin; then
  if [ "$OS" = "Windows_NT" ] && cygpath -m .>/dev/null 2>/dev/null ; then
    format=mixed
  else
    format=windows
  fi
  CLASSPATH=`cygpath --path --$format "$CLASSPATH"`
  SNOVA_HOME=`cygpath --path --$format "$SNOVA_HOME"`
fi

java -cp "$CLASSPATH" -DSNOVA_HOME="$SNOVA_HOME" org.snova.framework.launch.ApplicationLauncher cli