package vasconcelos.volleymatch.dto.live;

import vasconcelos.volleymatch.model.match.VolleyPosition;

/**
 * @param position court zone (1-6) at the time of the action
 * @param poste    player's role during the set (R4, Central, Passeur, Pointu, Libero), null for opponents
 */
public record LivePlayerDto(Long id, Integer jersey, String name, Integer position, VolleyPosition poste) {}
