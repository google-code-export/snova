
##Third proxy client
$TOR="127.0.0.1:9050";
$SSHD="socks5:127.0.0.1:7070";

##Reserved proxy implementation
$GAE="GAE";
$DIRECT="DIRECT";
$SEATTLE="SeattleGENI";
$GOOGLE="Google[TencentProxy:8080]";
$PHP="PHPTunnel";

#defined selected proxy
$SELECTED1=$GAE;
$SELECTED2=$SEATTLE;
$SELECTED3=$PHP;

def OnInit()
{
  ##Do your business
}

def OnRoutine()
{
   #Do your routine business here
   #eg: $output=system("./mysh.sh");
   #    log($output);
   #Tell the script engine to invoke this method 10s later
   return 10;
}


def SelectProxy(protocol, method, url, headers)
{
     #Set your https proxy here
     $host = GetHeader($headers, "Host");
     if $host~=".google" || $host~="ytimg."|| $host~="gstatic."|| $host~="developer.android.com" || $host~=".appspot.com"  
	    || $host~="www.youtube.com" || $host~="golang.org" 
     { 
        return $GOOGLE;
     }
       
     return $GAE;
}
