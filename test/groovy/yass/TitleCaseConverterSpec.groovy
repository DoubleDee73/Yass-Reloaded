/*
 * Yass Reloaded - Karaoke Editor
 * Copyright (C) 2009-2023 Saruta
 * Copyright (C) 2023 DoubleDee
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package yass

import spock.lang.Specification
import yass.titlecase.PhrasalVerbManager
import yass.titlecase.TitleCaseConverter

class TitleCaseConverterSpec extends Specification {
    def 'toApTitleCase should convert a title into title case'() {
        given:
        PhrasalVerbManager.setInstance(null);

        expect:
        TitleCaseConverter.toApTitleCase(input) == expectation

        where:
        input                                          || expectation
        'All This Time (Pick-Me-Up Song)'                || 'All This Time (Pick-Me-Up Song)'
        'Crazy in Love (feat. Jay-Z)'                  || 'Crazy in Love (feat. Jay-Z)'
        'erase/rewind'                                 || 'Erase/Rewind'
        'Over the Rainbow / Wonderful world'           || 'Over the Rainbow / Wonderful World'
        'Hand In My Pocket (acoustic Version)'         || 'Hand in My Pocket (Acoustic Version)'
        'you belong to me'                             || 'You Belong to Me'
        'hunting high and low'                         || 'Hunting High and Low'
        'The sun always shines on T.V.'                || 'The Sun Always Shines on T.V.'
        'somebody that i used to know (Feat. kimbra)'  || 'Somebody That I Used to Know (feat. Kimbra)'
        '(You Gotta) Fight For Your Right (To Party!)' || '(You Gotta) Fight for Your Right (To Party!)'
    }
}
