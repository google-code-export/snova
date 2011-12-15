# Copyright 2011 The Snappy-Go Authors. All rights reserved.
# Use of this source code is governed by a BSD-style
# license that can be found in the LICENSE file.

include $(GOROOT)/src/Make.inc
include ../Inc.mk

TARG=handler
GOFILES=\
	dispatch.go\


export GCIMPORTS=-I../codec/_obj -I../snappy/_obj -I../se/_obj -I../event/_obj -I../service/_obj -I$(APPENGINE_PKG)
include $(GOROOT)/src/Make.pkg

