
##Reserved proxy implementation
$GAE="GAE";
$DIRECT="DIRECT";
$GOOGLE="Google";
$HOSTS="Hosts";

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
     $host = GetHeader($headers, "Host");
     if $protocol == "https" && InHosts($host)
     {
        return $HOSTS;
     }
     
     if $host~=".google" || $host~="ytimg."|| $host~="gstatic."|| $host~="developer.android.com" || $host~=".appspot.com"  
	    || $host~="www.youtube.com" || $host~="golang.org" 
     { 
        return $GOOGLE;
     }
     
     if InHosts($host)
     {
        return $HOSTS;
     }
     return $GOOGLE;
}
