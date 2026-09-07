package com.bloodlink.request;

/**
 * A hospital as shown on a request.
 *
 * A name and a thana, not coordinates. The latitude and longitude behind it exist
 * for distance ranking in SPEC-007 and are not sent to a client.
 *
 * @param id    the hospital id
 * @param name  for example Dhaka Medical College Hospital
 * @param thana the thana it sits in
 */
public record HospitalSummary(Long id, String name, String thana) {
}
