/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.test.integration

import net.fabricmc.loom.test.util.ArchiveAssertionsTrait

import net.fabricmc.loom.test.util.MockMavenServerTrait
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import static java.lang.System.setProperty
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * This tests publishing signed artifacts to a maven server
 */
@Stepwise
class SignedProjectTest extends Specification implements MockMavenServerTrait, ArchiveAssertionsTrait {
	@Unroll
	@RestoreSystemProperties
	def "sign and publish lib #gradle"() {
		given:
			setProperty('loom.test.secretKey', PRIVATE_KEY)
		when:
			def result = create("publish", gradle)
		then:
			result.task(":publish").outcome == SUCCESS
		where:
			gradle             | _
			DEFAULT_GRADLE     | _
			PRE_RELEASE_GRADLE | _
	}

	@Override
	String name() {
		"signed"
	}

	static final String PRIVATE_KEY = """
-----BEGIN PGP PRIVATE KEY BLOCK-----

lQPGBGCm5LMBCADUeHXbe4TmP3qELtz6he7CLaVMFXqL/fU+M7GIrj0qtLU6pJ9v
KSbF3tQATKlU91zkQCIPg41VBlqkx85bOm0u7Nvv4JhWMqE+ZsNoNVXS2xQEyEIW
rX9Cd0/YibU2FpWOlo1l/UZPPD/lYUrkZEhgoKHMHP3SYb5Ohgpy4klTeQdXTRMF
q0IeyFynw3eqrdWmktOEApd7qeu/Hs1NEnZssSZQdAYB4R+tL/ePgIYXsViWvqbT
XDYfmd+AiRnACGtrt5P8tSmKhLPzth36cvqLXI+hSBGHu0PRfvQnfjn3CWq8AaIj
dLsmRYw8NedYZ5DgY3NIRMBkG561Uc1Kj5c7ABEBAAH+BwMCfh5aZV7x7zP/HCCP
WwbuNO9fLKss4J7sNVWdkX/ZsOy5OMLBql70F8PKEovObkYiAWsPUjrQ50VMhCUc
V2443FErPTC9A+5NsJ3Sx+BazbsUd9cprnJIW0tVGP4ij9j14A0VOogJUbzrxonQ
aCQ4OMJi5cxk/o2z/N5WDG92Qb/CxNlp6oxuUgdxXWdhWSpW6XBlBKfMsjK6acpo
gAQg+e8m0FCRrpd+vMoHFPYa0UdY8s2YH88te7YiQYYPf9FI3Uk8FeKRCqgRIwTr
fWd7Ubh2vK0h3ua3gyJm1aqQbIYVk/a2L1KF6tsuh1AYGbyXitx6cujSOukwz3xi
ej4CutY60PoIFihSiBBsRwpvcGr9RoYkJ8tKBqq67xTttYhdlBiedM4/05gdCglw
UXm7O1LVOro6vaI6RzP1hL5Q/OLkx4mxXtaNbsjP1/Urml7bB1aqzeoMXUSlSqB+
LHavKxonYcEj2cRKRg1v/t2UV0lXyimammJ5c4Hq49bLygYITrT9pL3n9OOmAYBL
/+uv7h640cYWeR8YBQ7jCbdaqP+bJNmIbKlLMZfcS49Bt/WM1kFa6CqvAyNFewuL
CnRbMcdteYGWYvSyvmzKDa7tQ6TILt9ZrJOGPTGrEM2zLIR8H6eDpzXSVwJb/0Nk
apaCzB9GqMDtYpEu+nMg1/EI2oSzj0Ng0pV+rAJr6oLc6Y0iesVKbwg2VgYgzF7U
CG9B15hPofUDKXb43Fig4nWieceDzGveY8vlFeSMBxzxhCRsXKP9oWogtNRJiJ9c
t+VkzBADEb82mnG/QuTBgCxceRBVu4Bg9tPGRSHjBZurtdkKvJqEq5ay/lGZ718b
3Za/hMzR6rakVfKdGs7A2HN68iCkX3cZYn+uaKT8aEUSXoSFZXfJqU3pVi2ql2MN
43RseA0og79mtCFsb29tLXRlc3QgPGxvb20tdGVzdEBleGFtcGxlLmNvbT6JAU4E
EwEKADgWIQSP20iY86Edwz6Qcq0M9Z/0ipBcJgUCYKbkswIbLwULCQgHAgYVCgkI
CwIEFgIDAQIeAQIXgAAKCRAM9Z/0ipBcJruCCADNydlXQRAr799Fr58zf9YGBcH5
7F3TQpzK2zd6iktFy9cjIu04pYtvdrEP+29hLmy1ibUBI3yx8HH1BxHm8Eu2ZTAn
b5EYkmF73CecdtSu3yL0tmk/4GLO6t2r/SN7imFnq9xKyTqJmtftQngBhgoA6KPk
4ZEkOA1MbVpaSjGy5H1U/XusH1UDA3SZWlOwrY3xO8TfycsR9BijtCqxTnuwNXzT
wWoDPJEzJM/KCs0aXRbwwWALcxqk6sevLwx4D4/k3xxEB8cf5cBJC8bJjnBz5FSi
WBVyzTF8wLkcSacL93kE6swpP+iNkIwkO4eoyTA2RmTJUcz/M0zWS7NEM8S0
=xl+8
-----END PGP PRIVATE KEY BLOCK-----"""
}