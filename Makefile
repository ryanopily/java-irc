SOURCE := src/com/ryansmarcil/*.java 
TARGET := $(patsubst src/%.java,target/%.class,$(SOURCE))

.PHONY: compile abjects clean
compile: $(TARGET)

abjects: target/com/ryansmarcil/Abjects.class
	java -cp target com.ryansmarcil.Abjects

clean:
	rm -rf target

target:
	mkdir target

target/%.class: src/%.java target
	javac -d target $<

target/com/ryansmarcil/Abjects.class: examples/com/ryansmarcil/Abjects.java $(SOURCE) target
	javac -sourcepath src -d target $< 
