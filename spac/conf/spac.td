
##Third proxy client
$TOR="127.0.0.1:9050";
$SSHD="socks5:127.0.0.1:7070";

##Reserved proxy implementation
$GAE="GAE";
$DIRECT="DIRECT";
$SEATTLE="SeattleGENI";
$GOOGLE="Google";
$C4="C4";

#defined selected proxy


def OnInit()
{
  ##Do your business
}

def OnRoutine()
{
   #Do your routine business here
   #eg: $output=System("./mysh.sh");
   #    Log($output);
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
     
     $contentLength = Int(GetHeader($headers, "Content-Length"));
     if $contentLength > 10240000
     {
         return $C4;
     }
     
     ##Only "GET/POST/PUT/DELETE/HEAD" supported in GAE
     if $method != "GET" && $method != "POST" 
        && $method != "PUT" && $method != "DELETE"
        && $method != "HEAD"
     {
        return $C4;
     }
     
     ##Request with body not allowed in GAE if it's not POST/PUT
     if $contentLength > 0
     {
        if $method != "POST" && $method != "PUT" 
        {
           return $C4;
        }
     }  
     return $GAE;
}
