package com.frotty27.nameplatebuilder.server;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

final class SettingsData {


    static final BuilderCodec<SettingsData> CODEC = BuilderCodec
            .builder(SettingsData.class, SettingsData::new)
            .append(new KeyedCodec<>("@Filter", Codec.STRING),
                    (SettingsData data, String value) -> data.filter = value,
                    (SettingsData data) -> data.filter)
            .add()
            .append(new KeyedCodec<>("Action", Codec.STRING),
                    (SettingsData data, String value) -> data.action = value,
                    (SettingsData data) -> data.action)
            .add()
            .append(new KeyedCodec<>("@Offset", Codec.STRING),
                    (SettingsData data, String value) -> data.offset = value,
                    (SettingsData data) -> data.offset)
            .add()
            .append(new KeyedCodec<>("@AdminFilter", Codec.STRING),
                    (SettingsData data, String value) -> data.adminFilter = value,
                    (SettingsData data) -> data.adminFilter)
            .add()
            .append(new KeyedCodec<>("@SepText", Codec.STRING),
                    (SettingsData data, String value) -> data.sepText = value,
                    (SettingsData data) -> data.sepText)
            .add()
            .append(new KeyedCodec<>("@PrefixText", Codec.STRING),
                    (SettingsData data, String value) -> data.prefixText = value,
                    (SettingsData data) -> data.prefixText)
            .add()
            .append(new KeyedCodec<>("@SuffixText", Codec.STRING),
                    (SettingsData data, String value) -> data.suffixText = value,
                    (SettingsData data) -> data.suffixText)
            .add()
            .append(new KeyedCodec<>("@BarEmptyText", Codec.STRING),
                    (SettingsData data, String value) -> data.barEmptyText = value,
                    (SettingsData data) -> data.barEmptyText)
            .add()
            .append(new KeyedCodec<>("@AdminDisFilter", Codec.STRING),
                    (SettingsData data, String value) -> data.adminDisFilter = value,
                    (SettingsData data) -> data.adminDisFilter)
            .add()
            .append(new KeyedCodec<>("@AdminServerName", Codec.STRING),
                    (SettingsData data, String value) -> data.adminServerName = value,
                    (SettingsData data) -> data.adminServerName)
            .add()
            .build();


    String filter;

    String action;

    String offset;

    String adminFilter;

    String adminDisFilter;

    String adminServerName;

    String sepText;

    String prefixText;

    String suffixText;

    String barEmptyText;
}
