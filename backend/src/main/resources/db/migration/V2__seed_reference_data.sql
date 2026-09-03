-- SPEC-002: reference data.
--
-- Forty Dhaka thanas and the four hospitals named in the backlog. Coordinates are
-- thana and campus centroid estimates accurate to roughly a kilometre, which is
-- adequate for ranking donors by distance to a hospital and inadequate for
-- anything navigational. Replacing them with an authoritative dataset is a new
-- migration; nothing else changes.

insert into thana (name, district, latitude, longitude) values
    ('Adabor',              'Dhaka', 23.772800, 90.358300),
    ('Airport',             'Dhaka', 23.851300, 90.405900),
    ('Badda',               'Dhaka', 23.780600, 90.426700),
    ('Banani',              'Dhaka', 23.793600, 90.404300),
    ('Bangshal',            'Dhaka', 23.719400, 90.401400),
    ('Bhashantek',          'Dhaka', 23.816700, 90.383300),
    ('Cantonment',          'Dhaka', 23.810300, 90.396000),
    ('Chawkbazar',          'Dhaka', 23.718300, 90.393600),
    ('Dakshinkhan',         'Dhaka', 23.872000, 90.420000),
    ('Darus Salam',         'Dhaka', 23.778900, 90.354200),
    ('Demra',               'Dhaka', 23.713200, 90.490000),
    ('Dhanmondi',           'Dhaka', 23.746100, 90.374200),
    ('Gendaria',            'Dhaka', 23.704700, 90.427600),
    ('Gulshan',             'Dhaka', 23.792500, 90.407800),
    ('Hazaribagh',          'Dhaka', 23.733300, 90.366700),
    ('Jatrabari',           'Dhaka', 23.710400, 90.433100),
    ('Kadamtali',           'Dhaka', 23.700000, 90.440000),
    ('Kafrul',              'Dhaka', 23.793600, 90.383300),
    ('Kamrangirchar',       'Dhaka', 23.716700, 90.366700),
    ('Khilgaon',            'Dhaka', 23.750000, 90.427800),
    ('Khilkhet',            'Dhaka', 23.828300, 90.420000),
    ('Kotwali',             'Dhaka', 23.710000, 90.405000),
    ('Lalbagh',             'Dhaka', 23.718300, 90.386100),
    ('Mirpur',              'Dhaka', 23.822300, 90.365400),
    ('Mohammadpur',         'Dhaka', 23.758300, 90.358300),
    ('Motijheel',           'Dhaka', 23.733000, 90.417200),
    ('Mugda',               'Dhaka', 23.738300, 90.435300),
    ('New Market',          'Dhaka', 23.733300, 90.383300),
    ('Pallabi',             'Dhaka', 23.823600, 90.365400),
    ('Paltan',              'Dhaka', 23.735000, 90.413300),
    ('Ramna',               'Dhaka', 23.738900, 90.395800),
    ('Rampura',             'Dhaka', 23.761400, 90.418100),
    ('Sabujbagh',           'Dhaka', 23.744200, 90.433100),
    ('Shahbagh',            'Dhaka', 23.738300, 90.395600),
    ('Shahjahanpur',        'Dhaka', 23.738300, 90.422200),
    ('Sher-e-Bangla Nagar', 'Dhaka', 23.771900, 90.378900),
    ('Shyampur',            'Dhaka', 23.694400, 90.436100),
    ('Sutrapur',            'Dhaka', 23.710600, 90.419400),
    ('Tejgaon',             'Dhaka', 23.763900, 90.394400),
    ('Uttara',              'Dhaka', 23.875900, 90.379500);

-- Hospitals carry their own coordinates rather than inheriting the thana centroid:
-- a hospital is a single building at a known address, and SPEC-007 measures
-- distance from it.
insert into hospital (name, thana_id, latitude, longitude)
select 'Dhaka Medical College Hospital', id, 23.725800, 90.397500
from thana where name = 'Chawkbazar';

insert into hospital (name, thana_id, latitude, longitude)
select 'Bangabandhu Sheikh Mujib Medical University', id, 23.739500, 90.396300
from thana where name = 'Shahbagh';

insert into hospital (name, thana_id, latitude, longitude)
select 'Square Hospitals Ltd', id, 23.752900, 90.383000
from thana where name = 'Tejgaon';

insert into hospital (name, thana_id, latitude, longitude)
select 'Evercare Hospital Dhaka', id, 23.815600, 90.426500
from thana where name = 'Badda';
