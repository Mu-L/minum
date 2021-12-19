##
# source directory
##
SRC_DIR := src

##
# test source directory
##
TST_SRC_DIR := test

##
# output directory
##
OUT_DIR := out

##
# sources
##
SRCS := $(wildcard $(SRC_DIR)/**/*.java)

##
# test sources
##
TST_SRCS := $(wildcard $(TST_SRC_DIR)/**/*.java)


##
# classes
## 
CLS := $(SRCS:$(SRC_DIR)/%.java=$(OUT_DIR)/%.class)

##
# test classes
## 
TST_CLS := $(TST_SRCS:$(TST_SRC_DIR)/%.java=$(OUT_DIR)/%.class)

# If Java home is defined (either from command-line
# argument or environment variable), add /bin/ to it
# to access the proper location of the java binaries
#
# otherwise, it will just remain an empty string
ifneq ($(JAVA_HOME),)
  JAVA_HOME := $(JAVA_HOME)/bin/
endif

# the name of our Java compiler
JC = $(JAVA_HOME)javac

# the name of the java runner
JAVA = $(JAVA_HOME)java

# the directory where we store the code coverage report
COV_DIR = coveragereport

# the directory we store the Jacoco coverage utilities
JAC_DIR = utils/jacoco

##
# suffixes
##
.SUFFIXES: .java

##
# targets that do not produce output files
##
.PHONY: all clean foo

##
# default target(s)
##
all: $(CLS)

# here is the target for the application code
$(CLS): $(OUT_DIR)/%.class: $(SRC_DIR)/%.java
	    $(JC) -d $(OUT_DIR)/ -cp $(SRC_DIR)/ $<

# here is the target for the test code
$(TST_CLS): $(OUT_DIR)/%.class: $(TST_SRC_DIR)/%.java
	    $(JC) -d $(OUT_DIR)/ -cp $(SRC_DIR)/ -cp $(TST_SRC_DIR)/ -cp $(OUT_DIR) $<


##
# clean up any output files
##
clean:
	    rm -fr $(OUT_DIR)
	    rm -fr $(COV_DIR)

# run the application
run: all
	    $(JAVA) -cp $(OUT_DIR) primary.Main

# run the tests
test: all $(TST_CLS)
	    $(JAVA) -cp $(OUT_DIR) primary.Tests

# If you want to obtain code coverage from running the tests
testcov: all $(TST_CLS)
	    $(JAVA) -javaagent:$(JAC_DIR)/jacocoagent.jar=destfile=$(COV_DIR)/jacoco.exec -cp $(OUT_DIR) primary.Tests
	    $(JAVA) -jar $(JAC_DIR)/jacococli.jar report $(COV_DIR)/jacoco.exec --html ./$(COV_DIR) --classfiles $(OUT_DIR) --sourcefiles $(SRC_DIR)

