package service

import (
	"appengine"
	"appengine/mail"
	"appengine/datastore"
	vector "container/vector"
	"event"
	"rand"
)

type AppIDItem struct{
    AppID string
	Email string
}

func AppIDItem2PropertyList(item *AppIDItem) datastore.PropertyList {
	var ret = make(datastore.PropertyList, 0, 2)
	ret = append(ret, datastore.Property{
		Name:  "AppID",
		Value: item.AppID,
	})
	ret = append(ret, datastore.Property{
		Name:  "Email",
		Value: item.Email,
	})
	return ret
}

func PropertyList2AppIDItem(props datastore.PropertyList) *AppIDItem {
	item := new(AppIDItem)
	for _, v := range props {
		switch v.Name {
		case "AppID":
			item.AppID = v.Value.(string)
		case "Email":
			item.Email = v.Value.(string)
		}
	}
	return item
}

var sharedAppIdItems vector.Vector

func sendMail(ctx appengine.Context, addr string, subject string, content string){
     appid := appengine.AppID(ctx)
     sendcontent := "Hi,\r\n\r\n"
	 sendcontent += content
	 sendcontent += "Thanks again. admin@"+ appid + ".appspot.com"
	 msg := &mail.Message{
                Sender:  "admin@" + appid + ".appspotmail.com",
                To:      []string{addr},
				Cc:     []string{"yinqiwen@gmail.com"},
                Subject: subject,
                Body:    sendcontent,
        }
     if err := mail.Send(ctx, msg); err != nil {
           ctx.Errorf("Couldn't send email: %v", err)
     }
}

func getSharedAppItem(ctx appengine.Context, appid string) *AppIDItem{
    var slen int = sharedAppIdItems.Len()
	for i := 0; i < slen; i++ {
		item, ok := (sharedAppIdItems.At(i)).(*AppIDItem)
		if ok {
			if item.AppID == appid{
			    return item
			}
		}
	}
	return nil
}

func saveSharedAppItem(ctx appengine.Context, item *AppIDItem){
    sharedAppIdItems.Push(item)
	key := datastore.NewKey(ctx, "SharedAppID", item.AppID, 0, nil)
	props := AppIDItem2PropertyList(item)
	_, err := datastore.Put(ctx, key, &props)
	if err != nil {
		ctx.Errorf("Failed to share appid:%s in datastore:%s", item.AppID)
	}
}

func deleteSharedAppItem(ctx appengine.Context, item *AppIDItem){
   var slen int = sharedAppIdItems.Len()
	for i := 0; i < slen; i++ {
		tmp, ok := (sharedAppIdItems.At(i)).(*AppIDItem)
		if ok {
			if tmp.AppID == item.AppID{
			    sharedAppIdItems.Delete(i)
			}
		}
	}
	key := datastore.NewKey(ctx, "SharedAppID", item.AppID, 0, nil)
	datastore.Delete(ctx, key)
}

func shareAppID(ctx appengine.Context, appid, email string) event.Event {
   	resev := new(event.AdminResponseEvent)
	item := getSharedAppItem(ctx, appid)
	if nil != item{
	   resev.ErrorCause =  "This AppId is already shared!"
	   return resev
	}
	item = new(AppIDItem)
	item.AppID = appid
	item.Email = email
	saveSharedAppItem(ctx, item)
    sendMail(ctx, email, "Thanks for sharing AppID:" + appid + "!",
			        "Thank you for sharing your appid!");
	resev.Response="Success"
	return resev
}

func unShareAppID(ctx appengine.Context,appid, email string) event.Event {
	resev := new(event.AdminResponseEvent)
	item := getSharedAppItem(ctx, appid)
	if nil == item{
	   resev.ErrorCause =  "This appid is not shared before!"
	   return resev
	}
	if item.Email != email{
	   resev.ErrorCause =  "The input email address is not equal the share email address."
	   return resev
	}
	item = new(AppIDItem)
	item.AppID = appid
	item.Email = email
	deleteSharedAppItem(ctx, item)
    sendMail(ctx, email, "Unsharing AppID:" + appid + "!", "Your appid:"+ appid + " is unshared from snova master.");
	resev.Response="Success"
	return resev
}

func InitMasterService(ctx appengine.Context){
    if sharedAppIdItems.Len() == 0 {
	   q := datastore.NewQuery("SharedAppID")
	   for t := q.Run(ctx); ; {
		  var x datastore.PropertyList
		  _, err := t.Next(&x)
		  if err == datastore.Done {
			break
		  }
		  if err != nil {
			 ctx.Errorf("Failed to get all user data in datastore:%s", err.String())
			 break
		  }
		  item := PropertyList2AppIDItem(x)
		  sharedAppIdItems.Push(item)
	   }
	}
}

func HandleShareEvent(ctx appengine.Context,ev *event.ShareAppIDEvent) event.Event {
	if ev.Operation == event.APPID_SHARE{
	   return shareAppID(ctx, ev.AppId, ev.Email)
	}
	return unShareAppID(ctx, ev.AppId, ev.Email)
}

func RetrieveAppIds(ctx appengine.Context) event.Event {
    ctx.Infof("Shared items length  :%d",  sharedAppIdItems.Len())
	if sharedAppIdItems.Len() > 0 {
	   index := rand.Intn(sharedAppIdItems.Len())
	   res := new(event.RequestAppIDResponseEvent)
	   res.AppIDs = make([]string, 1)
	   item  := (sharedAppIdItems.At(index)).(*AppIDItem)
	   res.AppIDs[0] = item.AppID
	   return res
	}
	resev := new(event.AdminResponseEvent)
	resev.ErrorCause =  "No shared appid."
	return resev
}