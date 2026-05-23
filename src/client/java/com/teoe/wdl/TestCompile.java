package com.teoe.wdl;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
public class TestCompile {
    public static void test() {
        ClientSendMessageEvents.ALLOW_CHAT.register((message) -> { return true; });
    }
}
