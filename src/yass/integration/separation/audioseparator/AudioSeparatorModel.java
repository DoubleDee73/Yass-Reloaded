package yass.integration.separation.audioseparator;

import yass.YassEnum;

import java.util.Arrays;
import java.util.List;

public enum AudioSeparatorModel implements YassEnum {
    // Top vocal separation models (SDR score in label where known)
    VOCALS_MBR("vocals_mel_band_roformer.ckpt",                         "MelBand Roformer Vocals by Kimberley Jensen (SDR 12.6) [default]"),
    KIM_VOCAL_2("Kim_Vocal_2.onnx",                                     "Kim Vocal 2 MDX-Net (SDR ~10)"),
    KIM_VOCAL_1("Kim_Vocal_1.onnx",                                     "Kim Vocal 1 MDX-Net"),
    BS_ROFORMER_1296("model_bs_roformer_ep_368_sdr_12.9628.ckpt",       "BS-Roformer Viperx 1296 (SDR 12.1 vocals, 16.3 instr)"),
    BS_ROFORMER_1297("model_bs_roformer_ep_317_sdr_12.9755.ckpt",       "BS-Roformer Viperx 1297 (SDR 11.8 vocals, 16.5 instr)"),
    MDX23C("MDX23C-8KFFT-InstVoc_HQ.ckpt",                             "MDX23C InstVoc HQ (SDR 10.6 vocals, 15.8 instr)"),
    UVR_MDX_NET_INST_HQ_3("UVR-MDX-NET-Inst_HQ_3.onnx",               "UVR MDX-NET Inst HQ 3"),
    // Karaoke models (separate lead vocals from backing)
    MBR_KARAOKE("mel_band_roformer_karaoke_aufr33_viperx_sdr_10.1956.ckpt", "MelBand Roformer Karaoke Aufr33/Viperx (SDR 10.2)"),
    UVR_KARA_2("UVR_MDXNET_KARA_2.onnx",                               "UVR MDX-NET Karaoke 2"),
    // Multi-stem models
    HTDEMUCS_FT("htdemucs_ft.yaml",                                     "HTDemucs fine-tuned (vocals, drums, bass, other)"),
    HTDEMUCS("htdemucs.yaml",                                           "HTDemucs (vocals, drums, bass, other)");

    private final String value;
    private final String label;

    AudioSeparatorModel(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public static String displayLabelForValue(String value) {
        if (value == null) {
            return fromValue(null).getLabel();
        }
        for (AudioSeparatorModel model : values()) {
            if (model.value.equalsIgnoreCase(value)) {
                return model.label;
            }
        }
        return value;
    }

    public static AudioSeparatorModel fromValue(String value) {
        if (value == null) return VOCALS_MBR;
        for (AudioSeparatorModel m : values()) {
            if (m.value.equalsIgnoreCase(value)) {
                return m;
            }
        }
        return VOCALS_MBR;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public List<YassEnum> listElements() {
        return Arrays.asList(values());
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
