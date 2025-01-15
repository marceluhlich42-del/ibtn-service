package de.klarverwaltung.kv_ibuttonservice;

public class IbuttonResult {
    int returnCode;
    String content;

    IbuttonResult(String s, int r){
        this.returnCode = r;
        this.content = s;
    }
}
