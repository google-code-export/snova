snova-gae  Read Me
Release 2011/12/18
http://snova.googlecode.com 

This file is part of snova-gae.                                   
                                                                  
snova is free software: you can redistribute it and/or modify 
it under the terms of the GNU General Public License as           
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.                   
                                                                  
snova is distributed in the hope that it will be useful,      
but WITHOUT ANY WARRANTY; without even the implied warranty of    
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the     
GNU General Public License for more details.                      
                                                                  
You should have received a copy of the GNU General Public License 
along with snova(client & server & plugin).  If not, see <http://www.gnu.org/licenses/>.

Dependencies
------------
1. You need to install JRE/JDK(1.6+).
2. You need to install Google App Engine SDK(Java/GO) (use the latest version)

INSTALL:
GAE server part：（GAE server部分）
 1. unzip snova-gae-[g|j]server-[version].zip
    任意目录下解压snova-gae-[g|j]server-[version].zip
 2. cd snova-gae-[g|j]server-[version] 
    进入解压的目录
  注意，Go/Java只需要部署一个即可；Go部分部署server需要在linux下执行
 3.For Java(jserver)
   modify war/WEB-INF/appengine-web.xml, change the element '<application>hyk-proxy-demo</application>'
       修改war/WEB-INF/appengine-web.xml， 将'<application>'值改为自己创建的appid
 4. execute appcfg.cmd/appcfg.sh update war & appcfg.cmd/appcfg.sh backends update war(make sure you are in the directory 'snova-gae-jserver-[version]')
       执行appcfg.cmd/appcfg.sh update war和 appcfg.cmd/appcfg.sh backends update war上传
 5.For Go(gserver in linux)
   modify app.yaml, change the element 'application:<appid>'
       修改app.yaml， 将'application:<appid>'值改为自己创建的appid
 6. execute appcfg.py update <dir> & appcfg.py backends update <dir>(make sure you are in the directory 'snova-gae-jserver-[version]')
       执行appcfg.py update <dir>和 appcfg.py backends update <dir>上传
 
    
Client part: （Client部分）
  1. cd snova-[version]/plugins/gae 
           进入snova gae plugin的目录
  3. modify conf/gae-client.xml, refer the comment for more information
           参照注释修改conf/gae-client.xml
  4. execute <SNOVA_HOME>/bin/start.bat(start.sh) to start the local server
             执行<SNOVA_HOME>/bin/start.bat(start.sh)启动local server

 
