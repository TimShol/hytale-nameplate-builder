package com.frotty27.nameplatebuilder.server;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Codec-backed data class for deserializing UI events from the editor page.
 *
 * <p>Each field maps to a named codec key sent by the client UI when the
 * player interacts with buttons, text fields, or other interactive elements.
 * Fields prefixed with {@code @} in the codec key (e.g. {@code @Filter})
 * are bidirectional (read/write); the {@code Action} field carries the
 * button click identifier.</p>
 *
 * <p>Extracted as a top-level class to avoid {@code NoClassDefFoundError}
 * from Hytale's {@code PluginClassLoader} when resolving inner classes
 * after extended server uptime.</p>
 *
 * @see NameplateBuilderPage
 */
final class SettingsData {

    /** Codec for serializing/deserializing this data between client and server. */
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

    /** Search/filter text from the available blocks panel. */
    String filter;
    /** Button click action identifier (e.g. {@code "Enable_0"}, {@code "NavNPCs"}). */
    String action;
    /** Vertical offset text field value. */
    String offset;
    /** Search/filter text from the admin Required panel. */
    String adminFilter;
    /** Search/filter text from the admin Disabled panel. */
    String adminDisFilter;
    /** Server display name text field from the admin Settings panel. */
    String adminServerName;
    /** Separator text field value from the separator popup. */
    String sepText;
    /** Prefix text field value from the format popup. */
    String prefixText;
    /** Suffix text field value from the format popup. */
    String suffixText;
    /** Bar empty character text field value from the format popup. */
    String barEmptyText;
}
