
##Third proxy client
$TOR="127.0.0.1:9050";
$SSHD="socks5:127.0.0.1:7070";

##Reserved proxy implementation
$GAE="GAE";
$DIRECT="DIRECT";
$SEATTLE="SeattleGENI";
$GOOGLE="Google";
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
     
     $contentLength = int(getHeader($headers, "Content-Length"));
     if $contentLength > 10240000
     {
         return $SSHD;
     }
     
     ##Only "GET/POST/PUT/DELETE/HEAD" supported in GAE
     if $method != "GET" && $method != "POST" 
        && $method != "PUT" && $method != "DELETE"
        && $method != "HEAD"
     {
        return $SSHD;
     }
     
     ##Request with body not allowed in GAE if it's not POST/PUT
     if $contentLength > 0
     {
        if $method != "POST" && $method != "PUT" 
        {
           return $SSHD;
        }
     }  
     return $GAE;
}
