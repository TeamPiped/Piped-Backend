package me.kavin.piped.utils.obj.federation;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class FederatedGeoBypassRequest {
    private String videoId;
    private List<String> allowedCountries;
}
