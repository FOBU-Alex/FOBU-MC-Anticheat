package com.example;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class NetworkHandler {
    // Identifier for the custom packet

    // Payload definition


    public record ModListPayload(List<String> mods) implements CustomPayload {
        public static final Id<ModListPayload> ID =
                new Id<>(Identifier.of("fobu-minecraft-anticheat", "mod_list"));

        public static final PacketCodec<PacketByteBuf, ModListPayload> CODEC = PacketCodec.of(
                // encoder
                (value, buf) -> {
                    buf.writeInt(value.mods.size());
                    for (String mod : value.mods) {
                        buf.writeString(mod);
                    }
                },
                // decoder
                (buf) -> {
                    int size = buf.readInt();
                    List<String> mods = new ArrayList<>();
                    for (int i = 0; i < size; i++) {
                        mods.add(buf.readString());
                    }
                    return new ModListPayload(mods);
                }
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record PackListPayload(List<String> packs) implements CustomPayload {
        public static final Id<PackListPayload> ID =
                new Id<>(Identifier.of("fobu-minecraft-anticheat", "pack_list"));

        public static final PacketCodec<PacketByteBuf, PackListPayload> CODEC = PacketCodec.of(
                (value, buf) -> {
                    buf.writeInt(value.packs.size());
                    for (String pack : value.packs) {
                        buf.writeString(pack);
                    }
                },
                (buf) -> {
                    int size = buf.readInt();
                    List<String> packs = new ArrayList<>();
                    for (int i = 0; i < size; i++) {
                        packs.add(buf.readString());
                    }
                    return new PackListPayload(packs);
                }
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}