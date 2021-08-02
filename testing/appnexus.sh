#!/bin/sh
set -e

PBS_HOST=prebid-server.rubiconproject.com

curl -X POST \
  http://$PBS_HOST/openrtb2/auction \
  -H 'User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.113 Safari/537.36' \
  -H 'Content-Type: application/json' \
  -H 'Referer: http://rubiconproject.com/prebid/gpt/prebidServer_example.html' \
  -H 'cache-control: no-cache' \
  -H 'X-Forwarded-For: 69.141.166.108' \
  --cookie 'uids=eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYXBwbmV4dXMiOiIxMjM0NSJ9LCJiZGF5IjoiMjAxNy0wOC0xNVQxOTo0Nzo1OS41MjM5MDgzNzZaIn0=' \
  -d '{
    "id": "467210fb-e696-462a-aa11-fee747fa8194",
    "source": {
      "tid": "467210fb-e696-462a-aa11-fee747fa8194"
    },
    "tmax": 3000,
    "imp": [
      {
        "id": "test_div_2",
        "ext": {
          "appnexus": {
            "video": {
              "skippable": true,
              "playback_methods": [
                "auto_play_sound_off"
              ]
            },
            "use_pmt_rule": false,
            "placement_id": 13144370
          }
        },
        "video": {
          "context": "outstream",
          "mimes": [
            "video/mp4",
            "video/x-flv"
          ],
          "api": [
            2
          ],
          "playerSize": [
            [
              640,
              480
            ]
          ],
          "maxduration": 30,
          "playbackmethod": [
            2
          ],
          "linearity": 1,
          "protocols": [
            1,
            2,
            3,
            4,
            5,
            6
          ]
        }
      }
    ],
    "test": 0,
    "ext": {
      "prebid": {
        "debug": 1,
        "targeting": {
          "includewinners": true,
          "includebidderkeys": false
        },
        "cache": {
          "vastxml": {
            "returnCreative": true
          }
        }
      }
    },
    "cur": [
      "USD"
    ],
    "site": {
      "publisher": {
        "id": "1001"
      },
      "page": "file:///home/dkhokhlin/Documents/For%20tests/PUC/Web%20Video/puc_test_video.html"
    },
    "device": {
      "w": 507,
      "h": 981
    },
    "regs": {
      "ext": {
        "gdpr": 0
      }
    },
    "user": {
      "ext": {}
    }
  }
'
