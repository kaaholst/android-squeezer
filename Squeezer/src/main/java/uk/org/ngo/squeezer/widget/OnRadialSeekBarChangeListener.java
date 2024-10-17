package uk.org.ngo.squeezer.widget;

public interface OnRadialSeekBarChangeListener {
    void onProgressChanged(RadialSeekBar seekBar, int progress);

    void onStartTrackingTouch(RadialSeekBar seekBar);

    void onStopTrackingTouch(RadialSeekBar seekBar);
}
